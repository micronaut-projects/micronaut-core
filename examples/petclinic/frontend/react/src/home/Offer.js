import React from 'react'
import config from "../config";

const Offer = ({offer}) => offer ? <div id="offers">


  <div className="jumbotron jumbotron-fluid" style={{
    backgroundImage: `url(${config.SERVER_URL}/images/${offer.pet.image})`,
    backgroundPosition: 'center',
    color: 'white'
  }}>
    <div className="container">
      <h1 className="display-4">{offer.pet.name}</h1>
      <p className="lead">{offer.description}</p>
      <p><strong>{offer.price}{offer.currency}</strong></p>
      <p>
        <small>{offer.pet.vendor} | {offer.pet.type}</small>
      </p>
    </div>
  </div>
</div> : <div className="jumbotron jumbotron-fluid">
  <div className="container">
    <h1 className="display-4">Loading...</h1>
  </div>
</div>

export default Offer;