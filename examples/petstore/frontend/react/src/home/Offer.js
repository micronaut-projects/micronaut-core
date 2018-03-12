import React from 'react'
import config from "../config"
import Price from "../display/Price"
import banner from '../images/banner.png'
import {Link} from "react-router-dom"
import {shape, object, string, number} from 'prop-types'

const Offer = ({offer}) => (offer && offer.pet) ? <div id="offers">


  <div className="jumbotron jumbotron-fluid offer-jumbotron" style={{
    backgroundImage: `url(${config.SERVER_URL}/images/${offer.pet.image})`
  }}>
    <div className="container">
      <Link style={{textDecoration: 'none', color: 'white'}} to={`/pets/${offer.pet.slug}`}>
      <h1 className="display-4">{offer.pet.name}</h1>
      <p className="lead">{offer.description}</p>
      <h3><Price price={offer.price} currency={offer.currency}/></h3>
      <p>
        <small>{offer.pet.vendor} | {offer.pet.type}</small>
      </p>
      </Link>
    </div>
  </div>
</div> : <div className="jumbotron jumbotron-fluid offer-jumbotron-fallback" style={{backgroundImage: `url(${banner})`}}>
  <div className="container">
  </div>
</div>

Offer.propTypes = {
  offer: shape({
    pet: object,
    description: string,
    currency: string,
    price: number
  })
}

export default Offer;