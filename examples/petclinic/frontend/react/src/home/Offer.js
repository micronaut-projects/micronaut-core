import React from 'react'
import config from "../config";
import Price from "../display/Price";
import banner from '../images/banner.png';

const Offer = ({offer}) => offer ? <div id="offers">


  <div className="jumbotron jumbotron-fluid offer-jumbotron" style={{
    backgroundImage: `url(${config.SERVER_URL}/images/${offer.pet.image})`
  }}>
    <div className="container">
      <h1 className="display-4">{offer.pet.name}</h1>
      <p className="lead">{offer.description}</p>
      <h3><Price price={offer.price} currency={offer.currency}/></h3>
      <p>
        <small>{offer.pet.vendor} | {offer.pet.type}</small>
      </p>
    </div>
  </div>
</div> : <div className="jumbotron jumbotron-fluid offer-jumbotron-fallback" style={{backgroundImage: `url(${banner})`}}>
  <div className="container">
  </div>
</div>

export default Offer;